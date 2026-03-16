#include <SFML/Network.hpp>
#include <algorithm>
#include <cstring>
#include <iostream>
#include <mutex>
#include <thread>
#include <vector>

// TODO: move `GameServer` into its own files (h/cpp).
// Note: This is compiled with SFML 2.6.2 in mind.
// It would work similarly with slightly older versions of SFML.
// A thourough rework is necessary for SFML 3.0.

class GameServer {
public:
    GameServer(unsigned short tcp_port, unsigned short udp_port) :
        m_tcp_port(tcp_port), m_udp_port(udp_port) {}

    // Binds to a port and then loops around.  For every client that connects,
    // we start a new thread receiving their messages.
    void tcp_start()
    {
        // BINDING
        sf::TcpListener listener;
        sf::Socket::Status status = listener.listen(m_tcp_port,
          sf::IpAddress("152.105.66.74"));
        if (status != sf::Socket::Status::Done)
        {
            std::cerr << "Error binding listener to port" << std::endl;
            return;
        }

        std::cout << "TCP Server is listening to port "
           << m_tcp_port
           << ", waiting for connections..."
           << std::endl;

        while (true)
        {
            // ACCEPTING
            sf::TcpSocket* client = new sf::TcpSocket;
            status = listener.accept(*client);
            if (status != sf::Socket::Status::Done)
            {
                delete client;
            } else {
                int num_clients = 0;
                {
                    std::lock_guard<std::mutex> lock(m_clients_mutex);
                    num_clients = m_clients.size();
                    m_clients.push_back(client);
                }
                std::cout << "New client connected: "
                    << client->getRemoteAddress()
                    << std::endl;
                {
                    // Send the id of the player aka the size of the clients
                    // vector.
                    status = client->send(&num_clients, 4);
                    if (status != sf::Socket::Status::Done)
                    {
                        std::cerr << "Could not send ID to client " <<
                            num_clients << std::endl;
                    }
                }
                // std::thread([&client, this] { handle_client(client); }).detach();
                std::thread(&GameServer::handle_client, this, client).detach();
            }
        }
        // No need to call close of the listener.
        // The connection is closed automatically when the listener object is out of scope.
    }

    // UDP echo server. Used to let the clients know our IP address in case
    // they send a UDP broadcast message.
    void udp_start() {
        // BINDING
        sf::UdpSocket socket;
        sf::Socket::Status status = socket.bind(m_udp_port);
        if (status != sf::Socket::Status::Done) {
            std::cerr << "Error binding socket to port " << m_udp_port << std::endl;
            return;
        }
        std::cout << "UDP Server started on port " << m_udp_port << std::endl;

        while (true) {
            // RECEIVING
            // sf::Packet packet;
            char data[1024];
            std::size_t received;
            sf::IpAddress sender;
            unsigned short senderPort;

            // status = socket.receive(packet, sender, senderPort);
            status = socket.receive(data, sizeof(data), received, sender, senderPort);
            if (status != sf::Socket::Status::Done) {
                std::cerr << "Error receiving data" << std::endl;
                continue;
            }

            // Only makes sense if the message is a string
            std::cout << "Received: " << data << " from " << sender << ":" <<
                senderPort << std::endl;

            // SENDING
            status = socket.send(data, received, sender, senderPort);
            if (status != sf::Socket::Status::Done) {
                std::cerr << "Error sending data" << std::endl;
            }
        }

        // Everything that follows only makes sense if we have a graceful way to exiting the loop.
        socket.unbind();
        std::cout << "Server stopped" << std::endl;
    }


private:
    unsigned short m_tcp_port;
    unsigned short m_udp_port;
    std::vector<sf::TcpSocket*> m_clients;
    std::mutex m_clients_mutex;

    // Loop around, receive messages from client and send them to all
    // the other connected clients.
    void handle_client(sf::TcpSocket* client)
    {
        while (true)
        {
            // RECEIVING
            char payload[1024];
            std::memset(payload, 0, 1024);
            size_t received;
            sf::Socket::Status status = client->receive(payload, 1024, received);
            if (status != sf::Socket::Status::Done)
            {
                std::cerr << "Error receiving message from client" << std::endl;
                break;
            } else {
                // Actually, there is no need to print the message if the message is not a string
                std::string message(payload);
                std::cout << "Received message: " << message << std::endl;
                broadcast_message(message, client);
            }
        }

        // Everything that follows only makes sense if we have a graceful way to exiting the loop.
        // Remove the client from the list when done
        {
            std::lock_guard<std::mutex> lock(m_clients_mutex);
            m_clients.erase(std::remove(m_clients.begin(), m_clients.end(), client),
                    m_clients.end());
        }
        delete client;
    }

    // Sends `message` from `sender` to all the other connected clients
    void broadcast_message(const std::string& message, sf::TcpSocket* sender)
    {
        // You might want to validate the message before you send it.
        // A few reasons for that:
        // 1. Make sure the message makes sense in the game.
        // 2. Make sure the sender is not cheating.
        // 3. First need to synchronise the players inputs (usually done in Lockstep).
        // 4. Compensate for latency and perform rollbacks (usually done in Ded Reckoning).
        // 5. Delay the sending of messages to make the game fairer wrt high ping players.
        // This is where you can write the authoritative part of the server.
        std::lock_guard<std::mutex> lock(m_clients_mutex);
        for (auto& client : m_clients)
        {
            if (client != sender)
            {
                // SENDING
                sf::Socket::Status status = client->send(message.c_str(), message.size() + 1) ;
                if (status != sf::Socket::Status::Done)
                {
                    std::cerr << "Error sending message to client" << std::endl;
                }
            }
        }
    }
};

int main()
{
    GameServer server(4300, 4301);
    std::thread udp_thread(&GameServer::udp_start, &server);
    // Alternative syntax: start the thread with a lambda
    // std::thread udp_thread( [&server] { server.udp_start(); } );
    std::this_thread::sleep_for(std::chrono::milliseconds(50)); // Not strictly necessary, just helps with the output
    server.tcp_start();
    udp_thread.join();
    return 0;
}
